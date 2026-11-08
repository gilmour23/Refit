"""Synthetic camera scenes only. No participant photos or card details are fixtures."""
import unittest
import cv2
import numpy as np
import measure_arm as M
from card_detection import find_local_card


def scene(style='dark'):
    image=np.full((600,800,3),(130,170,200),np.uint8)
    cv2.rectangle(image,(420,50),(780,560),(242,242,242),-1)
    arm=np.zeros(image.shape[:2],np.uint8)
    cv2.rectangle(arm,(80,350),(220,599),255,-1)
    image[arm>0]=(120,160,205)
    if style=='dark':cv2.rectangle(image,(480,120),(720,272),(42,42,42),-1)
    elif style=='band':
        cv2.rectangle(image,(480,120),(720,272),(218,218,218),-1)
        cv2.rectangle(image,(480,120),(720,246),(210,145,50),-1)
    elif style=='glare':
        cv2.rectangle(image,(480,120),(720,272),(180,180,180),-1)
        cv2.line(image,(485,260),(715,130),(239,239,239),25)
        cv2.rectangle(image,(480,120),(720,272),(140,140,140),3)
    return image,arm,np.array(cv2.cvtColor(np.uint8([[[130,170,200]]]),cv2.COLOR_BGR2LAB)[0,0],dtype=float)


class CardDetection(unittest.TestCase):
    def test_card_on_paper_is_not_lost_when_arm_background_is_wood(self):
        image,arm,bg=scene('dark')
        scale,quad=M.find_card(image,arm,bg)
        self.assertIsNotNone(scale,'card visible on the second background must be found')
        self.assertAlmostEqual(scale,85.6/240,delta=.025)
        np.testing.assert_allclose(np.asarray(quad).mean(axis=0),[600,196],atol=8)

    def test_contrasting_header_and_pale_card_band_use_whole_card(self):
        image,arm,bg=scene('band')
        scale,quad=M.find_card(image,arm,bg)
        self.assertIsNotNone(scale)
        self.assertAlmostEqual(scale,85.6/240,delta=.025)
        sides=cv2.minAreaRect(np.asarray(quad,np.float32))[1]
        self.assertAlmostEqual(max(sides)/min(sides),85.6/53.98,delta=.12)

    def test_glare_does_not_split_the_card_into_a_smaller_reference(self):
        image,arm,bg=scene('glare')
        scale,quad=M.find_card(image,arm,bg)
        self.assertIsNotNone(scale)
        self.assertAlmostEqual(scale,85.6/240,delta=.025)

    def test_paper_without_a_card_is_not_a_scale_reference(self):
        image,arm,bg=scene('none')
        self.assertEqual(M.find_card(image,arm,bg),(None,None))

    def test_blank_image_is_not_a_scale_reference(self):
        image=np.full((480,640,3),255,np.uint8)
        scale,quad=M.find_card(image,np.zeros(image.shape[:2],np.uint8),None)
        self.assertIsNone(scale)
        self.assertIsNone(quad)

    def test_two_competing_cards_need_operator_selection(self):
        image,arm,_=scene('dark')
        cv2.rectangle(image,(80,80),(320,232),(25,25,25),-1)
        self.assertIsNone(find_local_card(image,arm))

    def test_card_overlapping_arm_is_not_used_by_local_fallback(self):
        image,arm,_=scene('dark')
        cv2.rectangle(arm,(490,130),(710,260),255,-1)
        self.assertIsNone(find_local_card(image,arm))

    def test_hand_silhouette_without_card_is_not_a_reference(self):
        image=np.full((600,800,3),245,np.uint8)
        cv2.ellipse(image,(350,300),(85,100),0,0,360,(120,160,205),-1)
        for x,y in [(285,185),(325,155),(365,150),(405,180)]:
            cv2.rectangle(image,(x-12,y),(x+12,290),(120,160,205),-1)
            cv2.circle(image,(x,y),12,(120,160,205),-1)
        arm=np.zeros(image.shape[:2],np.uint8)
        cv2.rectangle(arm,(300,360),(390,599),255,-1)
        image[arm>0]=(120,160,205)
        self.assertIsNone(find_local_card(image,arm))

if __name__=='__main__':unittest.main()
